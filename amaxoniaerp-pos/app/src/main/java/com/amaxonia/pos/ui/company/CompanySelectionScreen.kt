package com.amaxonia.pos.ui.company

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.composition.AppGraph
import com.amaxonia.pos.domain.model.Company
import com.amaxonia.pos.ui.common.injectedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanySelectionScreen(
    viewModel: CompanySelectionViewModel =
        injectedViewModel {
            AppGraph.company.companySelectionViewModel()
        },
    onCompanySelected: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val subtitleColor = MaterialTheme.colorScheme.onSurface
    val filteredCompanies =
        remember(state.companies, searchQuery) { filterCompanies(state.companies, searchQuery) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { CompanySelectionTopBar(onBack = onBack) },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                CompanySelectionHeader(subtitleColor = subtitleColor)
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    if (state.error != null) {
                        CompanySelectionError(
                            error = state.error,
                            onRetry = viewModel::retry,
                        )
                    } else if (!state.isLoading) {
                        CompanyListItems(
                            searchQuery = searchQuery,
                            onQueryChange = { searchQuery = it },
                            filteredCompanies = filteredCompanies,
                            onSelect = { company ->
                                viewModel.selectCompany(company, onCompanySelected)
                            },
                        )
                    }
                }
            }
            if (state.isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

/** Filtra empresas por nombre o RUC (case-insensitive). */
private fun filterCompanies(
    companies: List<Company>,
    searchQuery: String,
): List<Company> {
    val query = searchQuery.trim()
    return if (query.isEmpty()) {
        companies
    } else {
        companies.filter { company ->
            company.name.contains(query, ignoreCase = true) ||
                company.ruc.contains(query, ignoreCase = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanySelectionTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Título y subtítulo de la pantalla de selección de empresa. */
@Composable
private fun CompanySelectionHeader(subtitleColor: Color) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Selecciona tu empresa",
        fontSize = 30.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "¿Dónde vas a trabajar hoy?",
        fontSize = 16.sp,
        color = subtitleColor,
    )
    Spacer(modifier = Modifier.height(24.dp))
}

/** Error de carga con reintento. */
@Composable
private fun CompanySelectionError(
    error: String?,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = error ?: "Error desconocido",
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Reintentar")
            }
        }
    }
}

/** Lista filtrable de empresas. */
@Composable
private fun CompanyListItems(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    filteredCompanies: List<Company>,
    onSelect: (Company) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CompanySearchField(
                query = searchQuery,
                onQueryChange = onQueryChange,
            )
        }
        if (filteredCompanies.isEmpty()) {
            item {
                Text(
                    text = "No hay resultados",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
        } else {
            items(filteredCompanies) { company ->
                CompanyItemModern(
                    company = company,
                    onClick = { onSelect(company) },
                )
            }
        }
    }
}

@Composable
private fun CompanySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Buscar empresa") },
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Limpiar búsqueda",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        shape = RoundedCornerShape(12.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
    )
}

@Composable
fun CompanyItemModern(
    company: Company,
    onClick: () -> Unit,
) {
    val iconBgColor = MaterialTheme.colorScheme.surfaceVariant
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onClick() }
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(50.dp)
                    .background(iconBgColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Domain,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = company.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "ID: ${company.ruc.ifBlank { "-" }}",
                fontSize = 13.sp,
                color = subtitleColor,
                fontWeight = FontWeight.Medium,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = "Seleccionar",
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
    }
}
