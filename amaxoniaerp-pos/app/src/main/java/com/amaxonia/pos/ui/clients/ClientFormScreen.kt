package com.amaxonia.pos.ui.clients

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.domain.model.ForeignIdType
import com.amaxonia.pos.domain.model.TaxpayerType
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.components.AmaxoniaDropdown
import com.amaxonia.pos.ui.common.components.AmaxoniaInput
import com.amaxonia.pos.ui.common.components.FormSection
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.theme.AmaxoniaBlue
import com.amaxonia.pos.ui.theme.BgLightGray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientFormScreen(
    clientId: String?,
    viewModel: ClientFormViewModel = injectedViewModel {
        ClientFormViewModel(
            DependencyContainer.clientRepository,
            DependencyContainer.addressCatalogRepository,
            DependencyContainer.clientTypeRepository,
            DependencyContainer.apiService,
            DependencyContainer.localStore,
            DependencyContainer.networkMonitor
        )
    },
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(clientId) {
        viewModel.loadClient(clientId)
    }

    Scaffold(
        containerColor = BgLightGray,
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
                        if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        else Icon(Icons.Default.Save, null, tint = AmaxoniaBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgLightGray)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (state.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = state.error ?: "Error desconocido",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            val client = state.client

            // CORRECCIÓN DE TILDES AQUÍ
            FormSection(title = "Información General") {
                AmaxoniaInput(
                    label = "Código",
                    value = client.code,
                    onValueChange = { viewModel.updateField { copy(code = it) } }
                )

                // Dropdown con nombre o Loading si está vacío pero tenemos ID
                val selectedClientType = state.clientTypes.firstOrNull { it.id == client.clientTypeId }
                val clientTypeLabel = selectedClientType?.name ?: if(client.clientTypeId != 0) "Cargando (${client.clientTypeId})..." else ""

                AmaxoniaDropdown(
                    label = "Tipo Cliente",
                    options = state.clientTypes.map { it.name },
                    selectedOption = clientTypeLabel,
                    enabled = true,
                    onOptionSelected = { name ->
                        state.clientTypes.firstOrNull { it.name == name }
                            ?.let(viewModel::onClientTypeSelected)
                    }
                )
                if (client.clientTypeId != 4) {
                    val isLocked = client.clientTypeId == 2 || client.clientTypeId == 3
                    AmaxoniaDropdown(
                        label = "Contribuyente",
                        options = TaxpayerType.values().map { it.label },
                        selectedOption = client.taxpayerType.label,
                        enabled = !isLocked,
                        onOptionSelected = { label ->
                            val type = TaxpayerType.values().find { it.label == label }!!
                            viewModel.onTaxpayerTypeChange(type)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // CORRECCIÓN DE TILDES AQUÍ
            FormSection(title = "Identificación") {
                if (client.clientTypeId == 4) {
                    AmaxoniaDropdown(
                        label = "Tipo Identificación Extranjera", // Con Tilde
                        options = ForeignIdType.values().map { it.label },
                        selectedOption = client.foreignIdType.label,
                        onOptionSelected = { label ->
                            val type = ForeignIdType.values().find { it.label == label }!!
                            viewModel.updateField { copy(foreignIdType = type) }
                        }
                    )
                    AmaxoniaInput(
                        label = "Número Identificación", // Con Tilde
                        value = client.foreignIdNumber,
                        onValueChange = { viewModel.updateField { copy(foreignIdNumber = it) } }
                    )
                } else {
                    if (client.taxpayerType == TaxpayerType.JURIDICO) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AmaxoniaInput(
                                label = "Documento",
                                value = client.ruc,
                                modifier = Modifier.weight(0.7f),
                                onValueChange = { viewModel.updateField { copy(ruc = it) } }
                            )
                            AmaxoniaInput(
                                label = "DV",
                                value = client.dv,
                                modifier = Modifier.weight(0.3f),
                                onValueChange = { viewModel.updateField { copy(dv = it) } }
                            )
                        }
                        AmaxoniaInput(
                            label = "Razón Social", // Con Tilde
                            value = client.firstName,
                            onValueChange = { viewModel.updateField { copy(firstName = it) } }
                        )
                        AmaxoniaInput(
                            label = "Nombre Comercial",
                            value = client.lastName,
                            onValueChange = { viewModel.updateField { copy(lastName = it) } }
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AmaxoniaInput(
                                label = "Cédula", // Con Tilde
                                value = client.cedula,
                                modifier = Modifier.weight(0.7f),
                                onValueChange = { viewModel.updateField { copy(cedula = it) } }
                            )
                            AmaxoniaInput(
                                label = "DV",
                                value = client.dv,
                                modifier = Modifier.weight(0.3f),
                                onValueChange = { viewModel.updateField { copy(dv = it) } }
                            )
                        }
                        AmaxoniaInput(
                            label = "Nombre",
                            value = client.firstName,
                            onValueChange = { viewModel.updateField { copy(firstName = it) } }
                        )
                        AmaxoniaInput(
                            label = "Apellido",
                            value = client.lastName,
                            onValueChange = { viewModel.updateField { copy(lastName = it) } }
                        )
                    }
                }
                AmaxoniaInput(
                    label = "Correo Electrónico", // Con Tilde
                    value = client.email,
                    onValueChange = { viewModel.updateField { copy(email = it) } }
                )
                AmaxoniaInput(
                    label = "Teléfono", // Con Tilde
                    value = client.phone,
                    onValueChange = { viewModel.updateField { copy(phone = it) } }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // CORRECCIÓN DE TILDES AQUÍ
            FormSection(title = "Dirección") { // Con Tilde
                if (client.clientTypeId == 4) {
                    AmaxoniaInput("País", client.country) { viewModel.updateField { copy(country = it) } }
                    AmaxoniaInput("Ciudad", client.city) { viewModel.updateField { copy(city = it) } }
                    AmaxoniaInput("Dirección exacta", client.addressDetail) { viewModel.updateField { copy(addressDetail = it) } }
                } else {
                    val selectedCountry = state.countries.firstOrNull { it.id == client.countryId }
                    val countryLabel = selectedCountry?.name ?: if(client.countryId != 0) "Cargando (${client.countryId})..." else ""

                    val selectedLevel1 = state.addressLevel1Options.firstOrNull { it.code == client.addressLevel1 }
                    val level1Label = selectedLevel1?.name ?: if(client.addressLevel1.isNotBlank()) client.addressLevel1 else ""

                    val selectedLevel2 = state.addressLevel2Options.firstOrNull { it.code == client.addressLevel2 }
                    val level2Label = selectedLevel2?.name ?: if(client.addressLevel2.isNotBlank()) client.addressLevel2 else ""

                    val selectedLevel3 = state.addressLevel3Options.firstOrNull { it.code == client.addressLevel3 }
                    val level3Label = selectedLevel3?.name ?: if(client.addressLevel3.isNotBlank()) client.addressLevel3 else ""

                    AmaxoniaDropdown(
                        label = "País", // Con Tilde
                        options = state.countries.map { it.name },
                        selectedOption = countryLabel,
                        enabled = true,
                        onOptionSelected = { name ->
                            state.countries.firstOrNull { it.name == name }?.let(viewModel::onCountrySelected)
                        }
                    )
                    AmaxoniaDropdown(
                        label = "Provincia",
                        options = state.addressLevel1Options.map { it.name },
                        selectedOption = level1Label,
                        enabled = state.countries.isNotEmpty(),
                        onOptionSelected = { name ->
                            state.addressLevel1Options.firstOrNull { it.name == name }
                                ?.let(viewModel::onAddressLevel1Selected)
                        }
                    )
                    AmaxoniaDropdown(
                        label = "Distrito",
                        options = state.addressLevel2Options.map { it.name },
                        selectedOption = level2Label,
                        enabled = client.addressLevel1.isNotBlank(),
                        onOptionSelected = { name ->
                            state.addressLevel2Options.firstOrNull { it.name == name }
                                ?.let(viewModel::onAddressLevel2Selected)
                        }
                    )
                    AmaxoniaDropdown(
                        label = "Corregimiento",
                        options = state.addressLevel3Options.map { it.name },
                        selectedOption = level3Label,
                        enabled = client.addressLevel2.isNotBlank(),
                        onOptionSelected = { name ->
                            state.addressLevel3Options.firstOrNull { it.name == name }
                                ?.let(viewModel::onAddressLevel3Selected)
                        }
                    )
                    AmaxoniaInput("Dirección exacta", client.addressDetail) { viewModel.updateField { copy(addressDetail = it) } }
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
            Button(
                onClick = { viewModel.saveClient(onSaveSuccess) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmaxoniaBlue)
            ) {
                Text("Guardar Cliente", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}