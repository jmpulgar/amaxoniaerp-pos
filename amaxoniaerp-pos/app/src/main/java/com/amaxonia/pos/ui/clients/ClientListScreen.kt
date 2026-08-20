package com.amaxonia.pos.ui.clients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.composition.AppGraph
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.ui.common.injectedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientListScreen(
    viewModel: ClientListViewModel =
        injectedViewModel {
            AppGraph.clients.clientListViewModel()
        },
    onBack: () -> Unit,
    onNavigateToForm: (String?) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val reachedBottom: Boolean by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem?.index != 0 && lastVisibleItem?.index == listState.layoutInfo.totalItemsCount - 1
        }
    }

    LaunchedEffect(reachedBottom) {
        if (reachedBottom) viewModel.loadMoreClients()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ClientsTopBar(title = "Clientes", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToForm(null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo Cliente")
            }
        },
    ) { padding ->
        ClientListContent(
            state = state,
            viewModel = viewModel,
            listState = listState,
            onNavigateToForm = onNavigateToForm,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun ClientListContent(
    state: ClientListState,
    viewModel: ClientListViewModel,
    listState: LazyListState,
    onNavigateToForm: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
    ) {
        ClientErrorCard(error = state.error, onRetry = viewModel::retry)
        ClientSearchField(
            value = state.searchQuery,
            onValueChange = viewModel::onSearchQueryChange,
        )
        if (state.isLoading && state.clients.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            ClientListItems(
                state = state,
                viewModel = viewModel,
                listState = listState,
                onNavigateToForm = onNavigateToForm,
            )
        }
    }
}

@Composable
private fun ClientListItems(
    state: ClientListState,
    viewModel: ClientListViewModel,
    listState: LazyListState,
    onNavigateToForm: (String?) -> Unit,
) {
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        items(state.clients, key = { it.id }) { client ->
            ClientListItem(
                client = client,
                photoUrl = viewModel.getClientPhotoUrl(client),
                onClick = { onNavigateToForm(client.id) },
            )
        }
        if (state.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun ClientListItem(
    client: Client,
    photoUrl: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClientAvatarBox(photoUrl = photoUrl)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "${client.firstName} ${client.lastName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Doc: ${if (client.ruc.isNotEmpty()) client.ruc else client.cedula}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Helper visual rápido para mostrar nombres en lugar de IDs
                Text(
                    text = clientTypeName(client.clientTypeId), // AHORA MUESTRA EL NOMBRE
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** Nombre legible del tipo de cliente del catálogo del backend. */
private fun clientTypeName(clientTypeId: Int): String =
    when (clientTypeId) {
        CLIENT_TYPE_CONTRIBUYENTE -> "Contribuyente"
        CLIENT_TYPE_CONSUMIDOR_FINAL -> "Consumidor Final"
        CLIENT_TYPE_GOBIERNO -> "Gobierno"
        CLIENT_TYPE_EXTRANJERO -> "Extranjero"
        else -> "Tipo $clientTypeId"
    }

/** IDs de tipo de cliente del catálogo del backend, mostrados con nombre fijo en la lista. */
private const val CLIENT_TYPE_CONTRIBUYENTE = 1
private const val CLIENT_TYPE_CONSUMIDOR_FINAL = 2
private const val CLIENT_TYPE_GOBIERNO = 3
private const val CLIENT_TYPE_EXTRANJERO = 4
