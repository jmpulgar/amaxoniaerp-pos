package com.amaxonia.pos.ui.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
                title = {
                    Text(
                        text = if (state.isEditMode) "Editar cliente" else "Nuevo cliente",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.saveClient(onSaveSuccess) },
                        enabled = !state.isSaving,
                        modifier = Modifier.size(48.dp),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Guardar cliente",
                                tint = MaterialTheme.colorScheme.primary,
                            )
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.error?.let { ClientFormError(it) }
            GeneralInformationSection(state, viewModel)
            IdentificationSection(state, viewModel)
            AddressSection(state, viewModel)
            SaveClientButton(
                isEditMode = state.isEditMode,
                isSaving = state.isSaving,
                onClick = { viewModel.saveClient(onSaveSuccess) },
            )
        }
    }
}

@Composable
private fun ClientFormError(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun GeneralInformationSection(
    state: ClientFormState,
    viewModel: ClientFormViewModel,
) {
    val client = state.client
    val selectedClientType = state.clientTypes.firstOrNull { it.id == client.clientTypeId }
    val clientTypeLabel =
        selectedClientType?.name
            ?: if (client.clientTypeId != 0) "Cargando (${client.clientTypeId})..." else ""

    FormSection(title = "Información general") {
        PosTextInput("Código", client.code) {
            viewModel.updateField { copy(code = it) }
        }
        PosDropdown(
            label = "Tipo de cliente",
            options = state.clientTypes.map { it.name },
            selectedOption = clientTypeLabel,
            onOptionSelected = { name ->
                state.clientTypes.firstOrNull { it.name == name }?.let(viewModel::onClientTypeSelected)
            },
        )
        if (client.clientTypeId != FOREIGN_CLIENT_TYPE) {
            val isLocked = client.clientTypeId == NATURAL_CLIENT_TYPE || client.clientTypeId == LEGAL_CLIENT_TYPE
            PosDropdown(
                label = "Contribuyente",
                options = TaxpayerType.values().map { it.label },
                selectedOption = client.taxpayerType.label,
                enabled = !isLocked,
                onOptionSelected = { label ->
                    TaxpayerType.values().find { it.label == label }?.let(viewModel::onTaxpayerTypeChange)
                },
            )
        }
    }
}

@Composable
private fun IdentificationSection(
    state: ClientFormState,
    viewModel: ClientFormViewModel,
) {
    val client = state.client
    FormSection(title = "Identificación") {
        when {
            client.clientTypeId == FOREIGN_CLIENT_TYPE -> ForeignIdentificationFields(state, viewModel)
            client.taxpayerType == TaxpayerType.JURIDICO -> LegalEntityFields(state, viewModel)
            else -> NaturalPersonFields(state, viewModel)
        }
        PosTextInput("Correo electrónico", client.email) {
            viewModel.updateField { copy(email = it) }
        }
        PosTextInput("Teléfono", client.phone) {
            viewModel.updateField { copy(phone = it) }
        }
    }
}

@Composable
private fun ForeignIdentificationFields(
    state: ClientFormState,
    viewModel: ClientFormViewModel,
) {
    val client = state.client
    PosDropdown(
        label = "Tipo de identificación extranjera",
        options = ForeignIdType.values().map { it.label },
        selectedOption = client.foreignIdType.label,
        onOptionSelected = { label ->
            ForeignIdType.values().find { it.label == label }?.let { type ->
                viewModel.updateField { copy(foreignIdType = type) }
            }
        },
    )
    PosTextInput("Número de identificación", client.foreignIdNumber) {
        viewModel.updateField { copy(foreignIdNumber = it) }
    }
}

@Composable
private fun LegalEntityFields(
    state: ClientFormState,
    viewModel: ClientFormViewModel,
) {
    val client = state.client
    DocumentAndDvRow(
        documentLabel = "Documento",
        document = client.ruc,
        dv = client.dv,
        onDocumentChange = { value -> viewModel.updateField { copy(ruc = value) } },
        onDvChange = { value -> viewModel.updateField { copy(dv = value) } },
    )
    PosTextInput("Razón social", client.firstName) {
        viewModel.updateField { copy(firstName = it) }
    }
    PosTextInput("Nombre comercial", client.lastName) {
        viewModel.updateField { copy(lastName = it) }
    }
}

@Composable
private fun NaturalPersonFields(
    state: ClientFormState,
    viewModel: ClientFormViewModel,
) {
    val client = state.client
    DocumentAndDvRow(
        documentLabel = "Cédula",
        document = client.cedula,
        dv = client.dv,
        onDocumentChange = { value -> viewModel.updateField { copy(cedula = value) } },
        onDvChange = { value -> viewModel.updateField { copy(dv = value) } },
    )
    PosTextInput("Nombre", client.firstName) {
        viewModel.updateField { copy(firstName = it) }
    }
    PosTextInput("Apellido", client.lastName) {
        viewModel.updateField { copy(lastName = it) }
    }
}

@Composable
private fun DocumentAndDvRow(
    documentLabel: String,
    document: String,
    dv: String,
    onDocumentChange: (String) -> Unit,
    onDvChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PosTextInput(
            label = documentLabel,
            value = document,
            modifier = Modifier.weight(DOCUMENT_FIELD_WEIGHT),
            onValueChange = onDocumentChange,
        )
        PosTextInput(
            label = "DV",
            value = dv,
            modifier = Modifier.weight(DV_FIELD_WEIGHT),
            onValueChange = onDvChange,
        )
    }
}

@Composable
private fun AddressSection(
    state: ClientFormState,
    viewModel: ClientFormViewModel,
) {
    FormSection(title = "Dirección") {
        if (state.client.clientTypeId == FOREIGN_CLIENT_TYPE) {
            ForeignAddressFields(state, viewModel)
        } else {
            LocalAddressFields(state, viewModel)
        }
    }
}

@Composable
private fun ForeignAddressFields(
    state: ClientFormState,
    viewModel: ClientFormViewModel,
) {
    val client = state.client
    PosTextInput("País", client.country) { viewModel.updateField { copy(country = it) } }
    PosTextInput("Ciudad", client.city) { viewModel.updateField { copy(city = it) } }
    PosTextInput("Dirección exacta", client.addressDetail) {
        viewModel.updateField { copy(addressDetail = it) }
    }
}

@Composable
private fun LocalAddressFields(
    state: ClientFormState,
    viewModel: ClientFormViewModel,
) {
    val client = state.client
    val countryLabel =
        state.countries.firstOrNull { it.id == client.countryId }?.name
            ?: if (client.countryId != 0) "Cargando (${client.countryId})..." else ""
    val level1Label = state.addressLevel1Options.firstOrNull { it.code == client.addressLevel1 }?.name ?: client.addressLevel1
    val level2Label = state.addressLevel2Options.firstOrNull { it.code == client.addressLevel2 }?.name ?: client.addressLevel2
    val level3Label = state.addressLevel3Options.firstOrNull { it.code == client.addressLevel3 }?.name ?: client.addressLevel3

    PosDropdown(
        label = "País",
        options = state.countries.map { it.name },
        selectedOption = countryLabel,
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
            state.addressLevel1Options.firstOrNull { it.name == name }?.let(viewModel::onAddressLevel1Selected)
        },
    )
    PosDropdown(
        label = "Distrito",
        options = state.addressLevel2Options.map { it.name },
        selectedOption = level2Label,
        enabled = client.addressLevel1.isNotBlank(),
        onOptionSelected = { name ->
            state.addressLevel2Options.firstOrNull { it.name == name }?.let(viewModel::onAddressLevel2Selected)
        },
    )
    PosDropdown(
        label = "Corregimiento",
        options = state.addressLevel3Options.map { it.name },
        selectedOption = level3Label,
        enabled = client.addressLevel2.isNotBlank(),
        onOptionSelected = { name ->
            state.addressLevel3Options.firstOrNull { it.name == name }?.let(viewModel::onAddressLevel3Selected)
        },
    )
    PosTextInput("Dirección exacta", client.addressDetail) {
        viewModel.updateField { copy(addressDetail = it) }
    }
}

@Composable
private fun SaveClientButton(
    isEditMode: Boolean,
    isSaving: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !isSaving,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = if (isEditMode) "Guardar cambios" else "Guardar cliente",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

private const val DOCUMENT_FIELD_WEIGHT = 0.72f
private const val DV_FIELD_WEIGHT = 0.28f
private const val NATURAL_CLIENT_TYPE = 2
private const val LEGAL_CLIENT_TYPE = 3
private const val FOREIGN_CLIENT_TYPE = 4
