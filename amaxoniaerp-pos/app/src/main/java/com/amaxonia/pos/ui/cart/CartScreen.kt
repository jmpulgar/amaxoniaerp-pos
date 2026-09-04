package com.amaxonia.pos.ui.cart
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.amaxonia.pos.composition.AppGraph
import com.amaxonia.pos.domain.model.ItemCarrito
import com.amaxonia.pos.domain.usecase.BigDecimalMoneyFormatter
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.common.components.QuantityStepper
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.payment.FinancialBreakdown
import com.amaxonia.pos.ui.payment.formatCurrencyLabel
import com.amaxonia.pos.ui.theme.PosExtraShapes
import com.amaxonia.pos.ui.theme.PosPalette
import com.amaxonia.pos.ui.theme.PosTextStyles
import com.amaxonia.pos.ui.theme.cartBrandGradient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    viewModel: CartViewModel =
        injectedViewModel {
            AppGraph.cart.cartViewModel()
        },
    onBack: () -> Unit,
    onCheckout: (Double) -> Unit,
    onSelectClient: () -> Unit, // Nuevo callback para ir a buscar cliente si no hay
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnCheckout by rememberUpdatedState(onCheckout)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CartUiEffect.Checkout -> currentOnCheckout(effect.total)
            }
        }
    }
    var showSellerSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val editState = androidx.compose.runtime.remember { CartEditState() }

    CartOrderSavedOverlay(state = state, viewModel = viewModel, onBack = onBack)
    CartEditDialogs(editState = editState, viewModel = viewModel)
    if (showSellerSheet) {
        CartSellerSheetOverlay(
            state = state,
            viewModel = viewModel,
            onDismiss = { showSellerSheet = false },
        )
    }

    Scaffold(
        topBar = {
            CartTopAppBar(
                state = state,
                onBack = onBack,
                onClearCart = { viewModel.onAction(CartUiAction.ClearCart) },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { CartBottomBarArea(state = state, viewModel = viewModel) },
    ) { padding ->
        CartScreenContent(
            state = state,
            viewModel = viewModel,
            actions =
                CartScreenActions(
                    onSelectClient = onSelectClient,
                    onChangeSeller = { showSellerSheet = true },
                    onStartEdit = editState::start,
                ),
            modifier = Modifier.padding(padding),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ─────────────────────────────────────────────────────────────────────────────
// Bottom bar: total (adaptive) + desglose colapsable + Guardar borrador / Cobrar.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun CartBottomBar(
    state: CartState,
    onSaveDraft: () -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var breakdownExpanded by remember { mutableStateOf(false) }
    val total = state.total

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
        shape = PosExtraShapes.BottomBarTop,
    ) {
        BoxWithConstraints {
            val compact = maxHeight < 480.dp
            Column(
                modifier =
                    Modifier
                        .padding(horizontal = 20.dp, vertical = if (compact) 8.dp else 12.dp)
                        .navigationBarsPadding(),
            ) {
                CartBottomTotalRow(
                    total = total,
                    secondaryTotal =
                        state.totalBsText
                            .takeIf { state.isMultiCurrency && it.isNotBlank() }
                            ?.let { "${formatCurrencyLabel(state.abrMonedaSecundaria)} $it" },
                    compact = compact,
                    expanded = breakdownExpanded,
                    onToggle = { breakdownExpanded = !breakdownExpanded },
                )

                AnimatedVisibility(
                    visible = breakdownExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        FinancialBreakdown(
                            snapshot = state.financialSnapshot,
                            totalFallback =
                                com.amaxonia.pos.domain.model.money.Money
                                    .fromDouble(total),
                            taxLabel = state.taxLabel.takeIf { it.isNotBlank() } ?: "Impuesto",
                            isMultiCurrency = state.isMultiCurrency,
                            tasa = state.tasa,
                            compact = true,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))
                CartBottomActions(
                    isMesaSession = state.sesionMesaId != null,
                    onSaveDraft = onSaveDraft,
                    onCheckout = onCheckout,
                )
            }
        }
    }
}

/** Fila del total con monto adaptivo, moneda secundaria y flecha que despliega el desglose. */
@Composable
private fun CartBottomTotalRow(
    total: Double,
    secondaryTotal: String?,
    compact: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else 180f,
        label = "breakdownArrow",
    )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onToggle)
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "Total",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (expanded) "Ocultar desglose" else "Ver desglose",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.End) {
                val totalStyle =
                    if (compact) {
                        MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
                    } else {
                        PosTextStyles.totalDisplay
                    }
                AdaptiveAmountText(
                    text = "$ ${String.format(java.util.Locale.getDefault(), "%.2f", total)}",
                    baseStyle = totalStyle,
                    color = MaterialTheme.colorScheme.primary,
                    options =
                        com.amaxonia.pos.ui.common.components
                            .AdaptiveAmountOptions(minFontSizeSp = 16f),
                )
                secondaryTotal?.takeIf { it.isNotBlank() }?.let { secondary ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Text(
                            secondary,
                            style = PosTextStyles.amountSecondary.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f),
                modifier = Modifier.size(30.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ExpandLess,
                        contentDescription = if (expanded) "Ocultar desglose" else "Ver desglose",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp).rotate(arrowRotation),
                    )
                }
            }
        }
    }
}

@Composable
private fun CartBottomActions(
    isMesaSession: Boolean = false,
    onSaveDraft: () -> Unit,
    onCheckout: () -> Unit,
) {
    if (isMesaSession) {
        Button(
            onClick = onCheckout,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            contentPadding = PaddingValues(horizontal = 14.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 6.dp),
        ) {
            Icon(Icons.Default.RestaurantMenu, null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Confirmar para Comanda",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                SaveDraftButton(onClick = onSaveDraft)
            }
            Box(modifier = Modifier.weight(1.35f)) {
                CheckoutButton(onClick = onCheckout)
            }
        }
    }
}

@Composable
private fun SaveDraftButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        Icon(Icons.Default.Save, null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "Guardar",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CheckoutButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = 14.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 6.dp),
    ) {
        Text(
            "Cobrar",
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                ),
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cliente + vendedor: panel consolidado con tipografía tokenizada e iconos
// diferenciados (Person / Storefront). Mismos clicks y estados habilitados.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun CartClientVendorPanel(
    state: CartState,
    onSelectClient: () -> Unit,
    onRemoveClient: () -> Unit,
    onChangeSeller: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        CartClientRow(state = state, onSelectClient = onSelectClient, onRemoveClient = onRemoveClient)
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f),
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        CartSellerRow(state = state, onChangeSeller = onChangeSeller)
    }
}

@Composable
private fun CartClientRow(
    state: CartState,
    onSelectClient: () -> Unit,
    onRemoveClient: () -> Unit,
) {
    val selectedClient = state.selectedClient
    if (selectedClient != null) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelectClient() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClientAvatar(
                clientPhotoUrl = state.selectedClientPhotoUrl,
                clientName = "${selectedClient.firstName} ${selectedClient.lastName}",
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(com.amaxonia.pos.ui.theme.SuccessGreen),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Cliente asignado",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${selectedClient.firstName} ${selectedClient.lastName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.40f),
            ) {
                IconButton(onClick = onRemoveClient, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Quitar cliente",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
    } else {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelectClient() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.60f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Asignar cliente a la venta",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Opcional para cliente genérico",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f),
            ) {
                Icon(
                    Icons.Default.Add,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(6.dp).size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun CartSellerRow(
    state: CartState,
    onChangeSeller: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = state.availableSellers.isNotEmpty()) { onChangeSeller() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.60f),
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Storefront, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Vendedor asignado",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = state.currentSeller?.nombre ?: "Sin vendedor",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.availableSellers.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(
                        Icons.Default.Autorenew,
                        contentDescription = "Cambiar vendedor",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Cambiar",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun CartErrorBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClientSucursalSelectorCard(
    sucursales: List<com.amaxonia.pos.domain.model.ClientBranch>,
    selectedSucursal: com.amaxonia.pos.domain.model.ClientBranch?,
    isRequiredMissing: Boolean,
    onSelect: (Int) -> Unit,
) {
    val hasMultiple = sucursales.size > 1

    Card(
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isRequiredMissing) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (hasMultiple) "Sucursal del cliente" else "Sucursal del cliente asignada",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isRequiredMissing) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (hasMultiple) {
                SucursalSelectorDropdown(
                    sucursales = sucursales,
                    selectedSucursal = selectedSucursal,
                    isRequiredMissing = isRequiredMissing,
                    onSelect = onSelect,
                )
                if (isRequiredMissing) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Este cliente tiene varias sucursales. Selecciona una para continuar.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Text(
                    text = selectedSucursal?.nombreSucursal ?: sucursales.firstOrNull()?.nombreSucursal.orEmpty(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (isRequiredMissing) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                )
                selectedSucursal?.direccion?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SucursalSelectorDropdown(
    sucursales: List<com.amaxonia.pos.domain.model.ClientBranch>,
    selectedSucursal: com.amaxonia.pos.domain.model.ClientBranch?,
    isRequiredMissing: Boolean,
    onSelect: (Int) -> Unit,
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedSucursal?.nombreSucursal.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            isError = isRequiredMissing,
            placeholder = { Text("Seleccionar sucursal") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(12.dp),
            modifier =
                Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            sucursales.forEach { sucursal ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(sucursal.nombreSucursal, fontWeight = FontWeight.SemiBold)
                            sucursal.direccion?.takeIf { it.isNotBlank() }?.let {
                                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    onClick = {
                        onSelect(sucursal.sucursalId)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ClientAvatar(
    clientPhotoUrl: String,
    clientName: String,
    modifier: Modifier = Modifier,
) {
    val initials = buildInitials(clientName)
    val gradient = cartBrandGradient()

    Box(
        modifier =
            modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center,
    ) {
        if (clientPhotoUrl.isBlank()) {
            Text(initials, color = PosPalette.FixedWhite, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        } else {
            SubcomposeAsyncImage(
                model = clientPhotoUrl,
                contentDescription = "Foto cliente",
                modifier = Modifier.fillMaxSize(),
                loading = { Text(initials, color = PosPalette.FixedWhite, fontWeight = FontWeight.Bold, fontSize = 13.5.sp) },
                error = { Text(initials, color = PosPalette.FixedWhite, fontWeight = FontWeight.Bold, fontSize = 13.5.sp) },
                success = { SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize()) },
            )
        }
    }
}

private fun buildInitials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    if (parts.isEmpty()) return "CL"
    return parts.take(2).joinToString(separator = "") { it.first().uppercase() }
}

@Composable
internal fun PromotionCartGroup(
    group: ItemCarrito.PromocionAgrupada,
    onRemove: () -> Unit,
    onQuantityChange: (Int) -> Unit,
) {
    val accent = if (group.promocionTipo == "KIT") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    var timesText by androidx.compose.runtime.remember(group.promocionId, group.items.firstOrNull()?.promocionVeces) {
        androidx.compose.runtime.mutableStateOf((group.items.firstOrNull()?.promocionVeces ?: 1).toString())
    }
    val times = timesText.toIntOrNull() ?: 0
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            PromotionGroupHeader(group = group, accent = accent, onRemove = onRemove)

            Spacer(Modifier.height(10.dp))
            PromotionGroupItems(group = group, accent = accent)

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total promoción", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accent.copy(alpha = 0.12f),
                ) {
                    Text(
                        BigDecimalMoneyFormatter.money(group.total),
                        fontWeight = FontWeight.ExtraBold,
                        color = accent,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            QuantityStepper(
                quantityText = timesText,
                onQuantityTextChange = { value ->
                    timesText = sanitizeQuantityInput(value)
                    timesText.toIntOrNull()?.takeIf { it >= 1 }?.let(onQuantityChange)
                },
                onDecrease = {
                    val next = ((timesText.toIntOrNull() ?: 1) - 1).coerceAtLeast(0)
                    if (next == 0) {
                        onRemove()
                    } else {
                        timesText = next.toString()
                        onQuantityChange(next)
                    }
                },
                onIncrease = {
                    val next = ((timesText.toIntOrNull() ?: 0) + 1).coerceAtLeast(1)
                    timesText = next.toString()
                    onQuantityChange(next)
                },
                onDone = {
                    if (times >= 1) onQuantityChange(times)
                },
                isError = timesText.isNotBlank() && times < 1,
                label = "Promociones",
            )
        }
    }
}

/** Encabezado de la promoción agrupada con su botón de quitar. */
@Composable
private fun PromotionGroupHeader(
    group: ItemCarrito.PromocionAgrupada,
    accent: androidx.compose.ui.graphics.Color,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accent,
                                MaterialTheme.colorScheme.primary,
                            ),
                        ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.LocalOffer, contentDescription = null, tint = PosPalette.FixedWhite, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("PROMOCIÓN ${group.promocionCodigo}", color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
            Text(
                group.promocionNombre,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "EL PRODUCTO ESTÁ CONFORMADO POR:",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.40f),
        ) {
            IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Quitar promoción",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

/** Líneas de producto que conforman la promoción. */
@Composable
private fun PromotionGroupItems(
    group: ItemCarrito.PromocionAgrupada,
    accent: androidx.compose.ui.graphics.Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        group.items.forEach { item ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.product.description,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Cant. ${String.format(java.util.Locale.getDefault(), "%.2f", item.quantityDecimal)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "$ ${String.format(java.util.Locale.getDefault(), "%.2f", item.total)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
        }
    }
}
