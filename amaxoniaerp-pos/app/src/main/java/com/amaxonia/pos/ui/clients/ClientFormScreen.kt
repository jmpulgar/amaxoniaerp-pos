package com.amaxonia.pos.ui.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.domain.model.ForeignIdType
import com.amaxonia.pos.domain.model.TaxpayerType
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.components.FormSection
import com.amaxonia.pos.ui.common.components.PosDropdown
import com.amaxonia.pos.ui.common.components.PosTextInput
import com.amaxonia.pos.ui.common.injectedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientFormScreen(
    clientId: String?,
    viewModel: ClientFormViewModel =
        injectedViewModel {
            ClientFormViewModel(
                DependencyContainer.clientRepository,
                DependencyContainer.addressCatalogRepository,
                DependencyContainer.clientFormCatalogSource,
            )
        },
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(clientId) {
        viewModel.loadClient(clientId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditMode) "Editar Cliente" else "Nuevo Cliente", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.saveClient(onSaveSuccess) }) {
                        if (state.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Save, null, tint = MaterialTheme.colorScheme.primary)
                        }
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
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            if (state.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        text = state.error ?: "Error desconocido",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            val client = state.client

            // CORRECCIÓN DE TILDES AQUÍ
            FormSection(title = "Información General") {
                PosTextInput(
                    label = "Código",
                    value = client.code,
                    onValueChange = { viewModel.updateField { copy(code = it) } },
                )

                // Dropdown con nombre o Loading si está vacío pero tenemos ID
                val selectedClientType = state.clientTypes.firstOrNull { it.id == client.clientTypeId }
                val clientTypeLabel =
                    selectedClientType?.name ?: if (client.clientTypeId != 0) "Cargando (${client.clientTypeId})..." else ""

                PosDropdown(
                    label = "Tipo Cliente",
                    options = state.clientTypes.map { it.name },
                    selectedOption = clientTypeLabel,
                    enabled = true,
                    onOptionSelected = { name ->
                        state.clientTypes
                            .firstOrNull { it.name == name }
                            ?.let(viewModel::onClientTypeSelected)
                    },
                )
                if (client.clientTypeId != 4) {
                    val isLocked = client.clientTypeId == 2 || client.clientTypeId == 3
                    PosDropdown(
                        label = "Contribuyente",
                        options = TaxpayerType.values().map { it.label },
                        selectedOption = client.taxpayerType.label,
                        enabled = !isLocked,
                        onOptionSelected = { label ->
                            TaxpayerType
                                .values()
                                .find { it.label == label }
                                ?.let(viewModel::onTaxpayerTypeChange)
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // CORRECCIÓN DE TILDES AQUÍ
            FormSection(title = "Identificación") {
                if (client.clientTypeId == 4) {
                    PosDropdown(
                        label = "Tipo Identificación Extranjera", // Con Tilde
                        options = ForeignIdType.values().map { it.label },
                        selectedOption = client.foreignIdType.label,
                        onOptionSelected = { label ->
                            ForeignIdType
                                .values()
                                .find { it.label == label }
                                ?.let { type -> viewModel.updateField { copy(foreignIdType = type) } }
                        },
                    )
                    PosTextInput(
                        label = "Número Identificación", // Con Tilde
                        value = client.foreignIdNumber,
                        onValueChange = { viewModel.updateField { copy(foreignIdNumber = it) } },
                    )
                } else {
                    if (client.taxpayerType == TaxpayerType.JURIDICO) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PosTextInput(
                                label = "Documento",
                                value = client.ruc,
                                modifier = Modifier.weight(0.7f),
                                onValueChange = { viewModel.updateField { copy(ruc = it) } },
                            )
                            PosTextInput(
                                label = "DV",
                                value = client.dv,
                                modifier = Modifier.weight(0.3f),
                                onValueChange = { viewModel.updateField { copy(dv = it) } },
                            )
                        }
                        PosTextInput(
                            label = "Razón Social", // Con Tilde
                            value = client.firstName,
                            onValueChange = { viewModel.updateField { copy(firstName = it) } },
                        )
                        PosTextInput(
                            label = "Nombre Comercial",
                            value = client.lastName,
                            onValueChange = { viewModel.updateField { copy(lastName = it) } },
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PosTextInput(
                                label = "Cédula", // Con Tilde
                                value = client.cedula,
                                modifier = Modifier.weight(0.7f),
                                onValueChange = { viewModel.updateField { copy(cedula = it) } },
                            )
                            PosTextInput(
                                label = "DV",
                                value = client.dv,
                                modifier = Modifier.weight(0.3f),
                                onValueChange = { viewModel.updateField { copy(dv = it) } },
                            )
                        }
                        PosTextInput(
                            label = "Nombre",
                            value = client.firstName,
                            onValueChange = { viewModel.updateField { copy(firstName = it) } },
                        )
                        PosTextInput(
                            label = "Apellido",
                            value = client.lastName,
                            onValueChange = { viewModel.updateField { copy(lastName = it) } },
                        )
                    }
                }
                PosTextInput(
                    label = "Correo Electrónico", // Con Tilde
                    value = client.email,
                    onValueChange = { viewModel.updateField { copy(email = it) } },
                )
                PosTextInput(
                    label = "Teléfono", // Con Tilde
                    value = client.phone,
                    onValueChange = { viewModel.updateField { copy(phone = it) } },
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // CORRECCIÓN DE TILDES AQUÍ
            FormSection(title = "Dirección") {
                // Con Tilde
                if (client.clientTypeId == 4) {
                    PosTextInput("País", client.country) { viewModel.updateField { copy(country = it) } }
                    PosTextInput("Ciudad", client.city) { viewModel.updateField { copy(city = it) } }
                    PosTextInput("Dirección exacta", client.addressDetail) { viewModel.updateField { copy(addressDetail = it) } }
                } else {
                    val selectedCountry = state.countries.firstOrNull { it.id == client.countryId }
                    val countryLabel = selectedCountry?.name ?: if (client.countryId != 0) "Cargando (${client.countryId})..." else ""

                    val selectedLevel1 = state.addressLevel1Options.firstOrNull { it.code == client.addressLevel1 }
                    val level1Label = selectedLevel1?.name ?: if (client.addressLevel1.isNotBlank()) client.addressLevel1 else ""

                    val selectedLevel2 = state.addressLevel2Options.firstOrNull { it.code == client.addressLevel2 }
                    val level2Label = selectedLevel2?.name ?: if (client.addressLevel2.isNotBlank()) client.addressLevel2 else ""

                    val selectedLevel3 = state.addressLevel3Options.firstOrNull { it.code == client.addressLevel3 }
                    val level3Label = selectedLevel3?.name ?: if (client.addressLevel3.isNotBlank()) client.addressLevel3 else ""

                    PosDropdown(
                        label = "País", // Con Tilde
                        options = state.countries.map { it.name },
                        selectedOption = countryLabel,
                        enabled = true,
                        onOptionSelected = { name ->
                            state.countries.firstOrNull { it.name == name }?.let(viewModel::onCountrySelected)
                        },
                    )
                    PosDropdown(
                        label = "Provincia",
                        options = state.addressLevel1Options.map { it.name },
                        selectedOption = level1Label,
                        enabled = state.countries.isNotEmpty(),
                        onOptionSelected = { name ->
                            state.addressLevel1Options
                                .firstOrNull { it.name == name }
                                ?.let(viewModel::onAddressLevel1Selected)
                        },
                    )
                    PosDropdown(
                        label = "Distrito",
                        options = state.addressLevel2Options.map { it.name },
                        selectedOption = level2Label,
                        enabled = client.addressLevel1.isNotBlank(),
                        onOptionSelected = { name ->
                            state.addressLevel2Options
                                .firstOrNull { it.name == name }
                                ?.let(viewModel::onAddressLevel2Selected)
                        },
                    )
                    PosDropdown(
                        label = "Corregimiento",
                        options = state.addressLevel3Options.map { it.name },
                        selectedOption = level3Label,
                        enabled = client.addressLevel2.isNotBlank(),
                        onOptionSelected = { name ->
                            state.addressLevel3Options
                                .firstOrNull { it.name == name }
                                ?.let(viewModel::onAddressLevel3Selected)
                        },
                    )
                    PosTextInput("Dirección exacta", client.addressDetail) { viewModel.updateField { copy(addressDetail = it) } }
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
            Button(
                onClick = { viewModel.saveClient(onSaveSuccess) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("Guardar Cliente", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
