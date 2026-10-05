package com.amaxonia.erp.ui.company

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.erp.domain.model.CompanySummary
import com.amaxonia.erp.ui.components.PosEmptyState
import com.amaxonia.erp.ui.components.PosFeedbackCard
import com.amaxonia.erp.ui.components.PosVisualTone
import com.amaxonia.erp.ui.components.isLandscape
import com.amaxonia.erp.ui.theme.PosExtraShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanySelectionScreen(
    viewModel: CompanySelectionViewModel,
    onCompanySelected: (com.amaxonia.erp.domain.model.CompanySession) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnCompanySelected by rememberUpdatedState(onCompanySelected)
    var searchQuery by remember { mutableStateOf("") }
    val isLandscape = isLandscape()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CompanySelectionEffect.CompanySelected -> currentOnCompanySelected(effect.session)
            }
        }
    }

    val filteredCompanies =
        remember(state.companies, searchQuery) {
            if (searchQuery.isBlank()) {
                state.companies
            } else {
                val q = searchQuery.trim().lowercase()
                state.companies.filter {
                    it.name.lowercase().contains(q) || (it.rif != null && it.rif.lowercase().contains(q))
                }
            }
        }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Seleccionar Empresa",
                        fontWeight = FontWeight.Bold,
                        style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp, vertical = if (isLandscape) 4.dp else 8.dp),
        ) {
            if (!isLandscape) {
                Text(
                    text = "Empresas Habilitadas",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tu cuenta tiene acceso a varias empresas. Selecciona con cuál deseas operar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Buscador moderno con surfaceVariant
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar por nombre o RIF/RUC...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpiar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = PosExtraShapes.InputRadius,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
            )

            if (state.error != null) {
                Spacer(modifier = Modifier.height(10.dp))
                PosFeedbackCard(
                    title = "Error al seleccionar empresa",
                    message = state.error!!,
                    tone = PosVisualTone.Error,
                )
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 14.dp))

            if (filteredCompanies.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    PosEmptyState(
                        icon = Icons.Default.Business,
                        title = "Sin resultados",
                        message = "No se encontraron empresas con el término de búsqueda ingresado.",
                    )
                }
            } else if (isLandscape) {
                // Cuadrícula de 2 columnas en modo horizontal
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filteredCompanies, key = { it.id }) { company ->
                        CompanySelectionItem(
                            company = company,
                            isSelected = state.selectedCompanyId == company.id,
                            isLoading = state.isLoading && state.selectedCompanyId == company.id,
                            onClick = { viewModel.selectCompany(company) },
                        )
                    }
                }
            } else {
                // Monocolumna en modo vertical
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filteredCompanies, key = { it.id }) { company ->
                        CompanySelectionItem(
                            company = company,
                            isSelected = state.selectedCompanyId == company.id,
                            isLoading = state.isLoading && state.selectedCompanyId == company.id,
                            onClick = { viewModel.selectCompany(company) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanySelectionItem(
    company: CompanySummary,
    isSelected: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val containerColor =
        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(PosExtraShapes.CardRadius)
                .clickable(enabled = !isLoading, onClick = onClick),
        shape = PosExtraShapes.CardRadius,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Business,
                        contentDescription = null,
                        tint =
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = company.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                company.rif?.let { rif ->
                    Text(
                        text = "RIF / RUC: $rif",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
