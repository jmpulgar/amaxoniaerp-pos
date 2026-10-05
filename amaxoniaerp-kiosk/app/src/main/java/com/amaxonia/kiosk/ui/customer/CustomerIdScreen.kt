package com.amaxonia.kiosk.ui.customer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerIdScreen(
    viewModel: CustomerIdViewModel,
    onCustomerConfirmed: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = "Datos de Facturación",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 48.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "¿Cómo deseas tu comprobante?",
                    style =
                        MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                        ),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Selecciona una opción para continuar con tu pago",
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        ),
                )

                Spacer(modifier = Modifier.height(36.dp))

                // Option 1: Consumidor Final (1-tap fast checkout)
                CustomerOptionCard(
                    title = "Consumidor Final",
                    subtitle = "Sin datos fiscales (Factura estándar inmediata)",
                    icon = Icons.Default.Person,
                    isSelected = !uiState.isCustomBilling,
                    onClick = {
                        viewModel.toggleCustomBilling(false)
                        viewModel.selectConsumidorFinal(onCustomerConfirmed)
                    },
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Option 2: Factura con Datos (RUC / Cédula)
                CustomerOptionCard(
                    title = "Factura con RUC / Cédula",
                    subtitle = "Ingresa tus datos fiscales para emitir factura a tu nombre o empresa",
                    icon = Icons.Default.Badge,
                    isSelected = uiState.isCustomBilling,
                    onClick = { viewModel.toggleCustomBilling(true) },
                )

                if (uiState.isCustomBilling) {
                    Spacer(modifier = Modifier.height(28.dp))
                    CustomBillingForm(
                        uiState = uiState,
                        onDocTypeChanged = viewModel::onDocTypeChanged,
                        onDocNumberChanged = viewModel::onDocNumberChanged,
                        onDvChanged = viewModel::onDvChanged,
                        onNameChanged = viewModel::onNameChanged,
                        onSubmit = { viewModel.submitCustomCustomer(onCustomerConfirmed) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor =
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val containerColor =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(if (isSelected) 3.dp else 1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color =
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint =
                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                )
            }
        }
    }
}

@Composable
private fun CustomBillingForm(
    uiState: CustomerIdUiState,
    onDocTypeChanged: (String) -> Unit,
    onDocNumberChanged: (String) -> Unit,
    onDvChanged: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(28.dp)) {
            Text(
                text = "Tipo de Documento",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("CEDULA" to "Cédula", "RUC" to "RUC", "PASAPORTE" to "Pasaporte").forEach { (key, label) ->
                    FilterChip(
                        selected = uiState.docType == key,
                        onClick = { onDocTypeChanged(key) },
                        label = { Text(label, fontWeight = FontWeight.SemiBold) },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = uiState.docNumber,
                    onValueChange = onDocNumberChanged,
                    label = { Text("Número de Documento / RUC *") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                )

                if (uiState.docType == "RUC" || uiState.docType == "CEDULA") {
                    OutlinedTextField(
                        value = uiState.dv,
                        onValueChange = onDvChanged,
                        label = { Text("DV") },
                        modifier = Modifier.width(90.dp),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChanged,
                label = { Text("Nombre o Razón Social *") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )

            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = uiState.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(
                    text = "Confirmar y Continuar",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}
