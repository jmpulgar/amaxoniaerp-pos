@file:Suppress(
    "CyclomaticComplexMethod",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "UnusedParameter",
)

package com.amaxonia.pos.ui.mesas

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Deck
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.core.device.DeviceClass
import com.amaxonia.pos.domain.model.mesas.Mesa
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.common.rememberDeviceClass

private const val PHONE_GRID_COLUMNS = 2
private const val TABLET_GRID_COLUMNS = 4

/**
 * Selección de área y mesa.
 *
 * Fase de solo consulta: al tocar una mesa se guarda la selección en memoria y se muestra en la
 * barra inferior. No se abre venta, no se crea pedido y no se envía ninguna escritura.
 *
 * [onTableConfirmed] queda cableado para la siguiente fase (apertura operativa de mesa).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreasMesasScreen(
    viewModel: AreasMesasViewModel =
        injectedViewModel {
            AreasMesasViewModel(
                DependencyContainer.areaRepository,
                DependencyContainer.cajaRepository,
                DependencyContainer.networkMonitor,
                DependencyContainer.selectedTableHolder,
                DependencyContainer.sesionMesaRepository,
            )
        },
    onBack: () -> Unit,
    onSelectCaja: () -> Unit,
    onTableConfirmed: (Mesa) -> Unit = {},
    /**
     * Fase 3 - Comanda: dispara la navegación a la pantalla de comanda cuando ya existe una
     * sesión activa para la mesa (sea porque se acaba de abrir o porque se recuperó).
     */
    onComenzarPedido: (mesa: Mesa, sesionId: Int) -> Unit = { _, _ -> },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val columns = if (rememberDeviceClass() == DeviceClass.TABLET) TABLET_GRID_COLUMNS else PHONE_GRID_COLUMNS
    val snackbarHostState = remember { SnackbarHostState() }

    // Mesa pendiente de apertura: se abre el dialog de "cantidad de personas" primero; solo al
    // confirmar se invoca `viewModel.onAbrirSesion(mesaId, cantidad)`. Si la mesa ya estaba
    // ocupada (según `estadosMesas`), en lugar del dialog pedimos la sesión activa para mostrarla.
    var pendingApertura by remember { mutableStateOf<Mesa?>(null) }

    // Fase 3 - Comanda: cuando se obtiene/recupera sesión para `pendingApertura`, disparamos
    // la navegación a la pantalla de comanda. Si la sesión llegó por otro camino (ej. otra caja),
    // el botón Continuar ya la navega directamente en onConfirm.
    LaunchedEffect(state.activeSesion?.id, pendingApertura?.id) {
        val sesion = state.activeSesion ?: return@LaunchedEffect
        val mesaPendiente = pendingApertura ?: return@LaunchedEffect
        if (sesion.mesaId == mesaPendiente.id && sesion.estado == "ABIERTA") {
            pendingApertura = null
            onComenzarPedido(mesaPendiente, sesion.id)
        }
    }

    LaunchedEffect(state.sesionError) {
        val message = state.sesionError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message, actionLabel = "Cerrar")
        viewModel.onDismissSesionError()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AreasMesasTopBar(
                branchName = state.sucursalNombre,
                isRefreshEnabled = !state.isLoadingAreas && !state.isLoadingMesas,
                viewMode = state.viewMode,
                canShowPlano = state.hasDistribucionValida,
                onViewModeChanged = viewModel::onViewModeChanged,
                onBack = onBack,
                onRefresh = viewModel::onRefresh,
            )
        },
        bottomBar = {
            state.selectedMesa?.let { mesa ->
                SelectedMesaBar(
                    mesaName = mesa.displayName,
                    areaName = state.selectedArea?.displayName.orEmpty(),
                    onClear = viewModel::onClearSelection,
                    onConfirm = {
                        val sesion = state.activeSesion
                        if (sesion != null && sesion.mesaId == mesa.id && sesion.estado == "ABIERTA") {
                            onComenzarPedido(mesa, sesion.id)
                        } else {
                            abrirORecuperarSesion(mesa, state, viewModel) { pendingApertura = it }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
        ) {
            if (state.isOffline && state.showingCachedData) {
                Spacer(modifier = Modifier.height(8.dp))
                OfflineConfigBanner()
            }
            AreasMesasBody(
                state = state,
                columns = columns,
                viewModel = viewModel,
                onSelectCaja = onSelectCaja,
            )
        }
    }

    // Loader superpuesto durante apertura/cierre/recuperación.
    if (state.isLoadingSesion) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    Column {
                        Text(
                            text = "Preparando la mesa",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Espera un momento…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    // Modal de "cantidad de personas" antes de abrir la sesión.
    pendingApertura?.let { mesa ->
        CantidadPersonasDialog(
            mesaLabel = "${mesa.displayName}${mesa.displayCode?.let { " · $it" }.orEmpty()}",
            onConfirm = { cantidad ->
                viewModel.onAbrirSesion(mesa.id, cantidad)
                pendingApertura = null
            },
            onDismiss = { pendingApertura = null },
        )
    }
}

/**
 * Decide qué hacer al pulsar "Continuar" en la barra de la mesa seleccionada:
 * - Si la mesa ya está ocupada: recupera y muestra la sesión activa (en fases siguientes abrirá
 *   la comanda).
 * - Si está disponible o no hay datos hidratados: pide cantidad de personas y abre sesión.
 */
private fun abrirORecuperarSesion(
    mesa: Mesa,
    state: AreasMesasState,
    viewModel: AreasMesasViewModel,
    setPending: (Mesa?) -> Unit,
) {
    if (state.hasEstadosHidratados && state.isOcupada(mesa.id)) {
        viewModel.onRecuperarSesionActiva(mesa.id)
    } else {
        setPending(mesa)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AreasMesasTopBar(
    branchName: String,
    isRefreshEnabled: Boolean,
    viewMode: SalonViewMode,
    canShowPlano: Boolean,
    onViewModeChanged: (SalonViewMode) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Áreas y mesas",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                branchName.takeIf { it.isNotBlank() }?.let { branch ->
                    Text(
                        text = branch,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        actions = {
            // Toggle Plano/Lista. Solo se permite cambiar a Plano si el área trae distribución
            // válida; el ViewModel ya ignora el cambio en caso contrario, pero el botón se
            // deshabilita para reflejar el estado al usuario.
            if (canShowPlano) {
                FilledTonalIconButton(onClick = {
                    onViewModeChanged(
                        if (viewMode == SalonViewMode.PLANO) SalonViewMode.LISTA else SalonViewMode.PLANO,
                    )
                }) {
                    Icon(
                        imageVector =
                            if (viewMode == SalonViewMode.PLANO) {
                                Icons.Default.GridView
                            } else {
                                Icons.Default.Map
                            },
                        contentDescription =
                            if (viewMode == SalonViewMode.PLANO) {
                                "Ver como lista"
                            } else {
                                "Ver como plano"
                            },
                    )
                }
            }
            // Deshabilitado mientras hay consultas en vuelo: evita relanzarlas con pulsaciones repetidas.
            IconButton(onClick = onRefresh, enabled = isRefreshEnabled) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Actualizar áreas y mesas",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun AreasMesasBody(
    state: AreasMesasState,
    columns: Int,
    viewModel: AreasMesasViewModel,
    onSelectCaja: () -> Unit,
) {
    when {
        state.requiresCaja ->
            MesasInfoState(
                icon = Icons.Default.PointOfSale,
                title = "Selecciona una caja",
                message = "Las áreas se muestran según la sucursal de la caja activa.",
                modifier = Modifier.fillMaxSize(),
                action = InfoAction(label = "Seleccionar caja", onClick = onSelectCaja),
            )

        state.areasError != null -> {
            Spacer(modifier = Modifier.height(16.dp))
            MesasErrorState(message = state.areasError, onRetry = viewModel::onRetryAreas)
        }

        state.isLoadingAreas && state.areas.isEmpty() ->
            MesasLoadingState(modifier = Modifier.fillMaxSize())

        state.isAreasEmpty ->
            MesasInfoState(
                icon = Icons.Default.Deck,
                title = "Esta sucursal no tiene áreas",
                message = "Crea áreas para la sucursal en el sistema administrativo.",
                modifier = Modifier.fillMaxSize(),
            )

        else -> {
            Spacer(modifier = Modifier.height(8.dp))
            AreaChipRow(
                areas = state.areas,
                selectedAreaId = state.selectedAreaId,
                enabled = state.areAreaChipsEnabled,
                onSelect = viewModel::onAreaSelected,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            MesaStateLegend(
                totalMesas = state.mesas.size,
                estados = state.estadosMesas.values,
                isLoading = state.isLoadingEstados,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            MesasContent(
                state = state,
                columns = columns,
                onMesaClick = viewModel::onMesaSelected,
                onRetryMesas = viewModel::onRetryMesas,
            )
        }
    }
}

@Composable
private fun MesasContent(
    state: AreasMesasState,
    columns: Int,
    onMesaClick: (Int) -> Unit,
    onRetryMesas: () -> Unit,
) {
    when {
        state.mesasError != null ->
            MesasErrorState(message = state.mesasError, onRetry = onRetryMesas)

        state.isLoadingMesas && state.mesas.isEmpty() ->
            MesasLoadingState(modifier = Modifier.fillMaxSize())

        state.isMesasEmpty ->
            MesasInfoState(
                icon = Icons.Default.TableRestaurant,
                title = "Esta área no tiene mesas",
                message = "Configura mesas para esta área en el sistema administrativo.",
                modifier = Modifier.fillMaxSize(),
            )

        state.canShowPlano -> {
            // Plano visual con zoom/pan y rotación. Solo lectura: tapping selecciona la mesa.
            SalonPlan(
                mesas = state.mesas,
                lienzo = state.lienzo,
                imagenUrl = state.imagenUrl,
                selectedMesaId = state.selectedMesaId,
                onMesaClick = onMesaClick,
                estadosByMesaId = state.estadosMesas,
                modifier = Modifier.fillMaxSize(),
            )
        }

        else ->
            // Fallback a lista/cuadrícula: áreas sin distribución válida, mesas apiladas en
            // (0,0) o porque el usuario cambió a la vista de lista manualmente.
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.mesas, key = { it.id }) { mesa ->
                    MesaCard(
                        mesa = mesa,
                        isSelected = mesa.id == state.selectedMesaId,
                        estadoOperativo = if (state.hasEstadosHidratados) state.estadoOperativo(mesa.id) else null,
                        onClick = { onMesaClick(mesa.id) },
                    )
                }
            }
    }
}

/**
 * Confirmación visual de la mesa elegida. "Continuar" es el enganche de la fase siguiente: hoy
 * no abre venta ni modifica la mesa.
 */
@Composable
private fun SelectedMesaBar(
    mesaName: String,
    areaName: String,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.TableRestaurant,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mesaName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = areaName.ifBlank { "Mesa seleccionada" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cambiar mesa")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1.4f),
                ) {
                    Text("Continuar")
                }
            }
        }
    }
}
