package com.amaxonia.pos.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.composition.AppGraph
import com.amaxonia.pos.domain.model.BestSellerProduct
import com.amaxonia.pos.domain.model.SummaryStats
import com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.theme.PosPalette
import com.amaxonia.pos.ui.theme.paymentMethodColor
import java.util.Locale

private const val BEST_SELLER_LIMIT = 10
private const val BEST_SELLER_AMOUNT_WEIGHT = 0.6f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = injectedViewModel { AppGraph.reports.reportsViewModel() },
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Reportes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Actualizar",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        when {
            state.isLoading && state.summary == null -> ReportsLoading(Modifier.padding(padding))
            state.error != null && state.summary == null -> {
                ReportsError(
                    message = state.error ?: "Error desconocido",
                    onRetry = viewModel::retry,
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        ReportFiltersSection(
                            selectedPeriod = state.selectedPeriod,
                            onlyActiveCaja = state.onlyActiveCaja,
                            activeCajaName = state.activeCajaName,
                            onSelectPeriod = viewModel::selectPeriod,
                            onToggleOnlyActiveCaja = viewModel::toggleOnlyActiveCaja,
                        )
                    }
                    state.summary?.let { summary ->
                        item {
                            HeroSummaryCard(
                                summary = summary,
                                period = state.selectedPeriod,
                            )
                        }
                        item { OperationalMetricsRow(summary) }
                        item {
                            PaymentMethodsCard(
                                items = state.paymentBreakdown,
                                currency = summary.moneda,
                            )
                        }
                    }
                    item { BestSellersCard(state.bestSellers) }
                }
            }
        }
    }
}

@Composable
private fun ReportFiltersSection(
    selectedPeriod: ReportPeriod,
    onlyActiveCaja: Boolean,
    activeCajaName: String?,
    onSelectPeriod: (ReportPeriod) -> Unit,
    onToggleOnlyActiveCaja: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Quick period chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReportPeriod.entries.forEach { period ->
                val isSelected = selectedPeriod == period
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectPeriod(period) },
                    label = {
                        Text(
                            text = period.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }

        // Active cash drawer vs All cash drawers filter
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = onlyActiveCaja,
                onClick = onToggleOnlyActiveCaja,
                leadingIcon = {
                    Icon(
                        imageVector = if (onlyActiveCaja) Icons.Rounded.PointOfSale else Icons.Rounded.Storefront,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                label = {
                    Text(
                        text = if (onlyActiveCaja) {
                            "Mi caja (${activeCajaName ?: "Actual"})"
                        } else {
                            "Todas las cajas"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (onlyActiveCaja) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                trailingIcon = {
                    if (onlyActiveCaja) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun ReportsLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = "Cargando reportes...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReportsError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Button(onClick = onRetry) {
                    Text("Reintentar")
                }
            }
        }
    }
}

@Composable
private fun HeroSummaryCard(
    summary: SummaryStats,
    period: ReportPeriod,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.AttachMoney,
                    contentDescription = null,
                    tint = PosPalette.FixedWhite.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Ventas netas · ${period.label}",
                    style = MaterialTheme.typography.labelLarge,
                    color = PosPalette.FixedWhite.copy(alpha = 0.85f),
                )
            }

            AdaptiveAmountText(
                text = money(summary.moneda, summary.netSales),
                modifier = Modifier.fillMaxWidth(),
                baseStyle =
                    MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                color = PosPalette.FixedWhite,
                options = AdaptiveAmountOptions(minFontSizeSp = 20f),
            )

            // Multicurrency secondary reference if applicable
            if (summary.netSalesRef != null && !summary.abrMonedaSecundaria.isNullOrBlank()) {
                Text(
                    text = "Ref: ${summary.abrMonedaSecundaria} ${money("", summary.netSalesRef)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = PosPalette.FixedWhite.copy(alpha = 0.85f),
                )
            }

            HorizontalDivider(
                color = PosPalette.FixedWhite.copy(alpha = 0.2f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SummaryValue(
                    label = "Ventas brutas",
                    value = money(summary.moneda, summary.grossSales),
                    modifier = Modifier.weight(1f),
                )
                SummaryValue(
                    label = "Descuentos",
                    value = money(summary.moneda, summary.discounts),
                    modifier = Modifier.weight(1f),
                )
                SummaryValue(
                    label = "Ticket prom.",
                    value = money(summary.moneda, summary.ticketPromedio),
                    modifier = Modifier.weight(1f),
                    alignEnd = true,
                )
            }
        }
    }
}

@Composable
private fun SummaryValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = PosPalette.FixedWhite.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AdaptiveAmountText(
            text = value,
            modifier = Modifier.fillMaxWidth(),
            baseStyle =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
                ),
            color = PosPalette.FixedWhite,
            options = AdaptiveAmountOptions(minFontSizeSp = 10f),
        )
    }
}

@Composable
private fun OperationalMetricsRow(summary: SummaryStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Facturas Pagadas
        OperationalMiniCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.Receipt,
            iconTint = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.surface,
            label = "Facturas emitidas",
            value = summary.totalPaid.toString(),
            detail = "${summary.totalTransactions} transacciones",
        )

        // Auditoría de Anulaciones
        val hasCancellations = summary.totalCancelled > 0
        OperationalMiniCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.Cancel,
            iconTint = if (hasCancellations) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
            containerColor = if (hasCancellations) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            label = "Anuladas",
            value = summary.totalCancelled.toString(),
            detail = if (hasCancellations && summary.cancellations > 0) {
                "-${money(summary.moneda, summary.cancellations)}"
            } else {
                "Sin anulaciones"
            },
            detailColor = if (hasCancellations) MaterialTheme.colorScheme.error else null,
        )
    }
}

@Composable
private fun OperationalMiniCard(
    icon: ImageVector,
    iconTint: Color,
    containerColor: Color,
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
    detailColor: Color? = null,
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = detailColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PaymentMethodsCard(
    items: List<PaymentBreakdownItem>,
    currency: String,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionTitle(
                icon = Icons.Rounded.AccountBalanceWallet,
                text = "Ventas por método de pago",
            )

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Sin cobros registrados en este período",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items.forEachIndexed { index, item ->
                    PaymentMethodRow(item = item, currency = currency)
                    if (index < items.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentMethodRow(
    item: PaymentBreakdownItem,
    currency: String,
) {
    val methodColor = paymentMethodColor(item.name)
    val percentageFormatted = "${(item.percentage * 100).toInt()}%"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(methodColor),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${item.count} ${if (item.count == 1) "pago" else "pagos"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = money(currency, item.amount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = percentageFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = methodColor,
                )
            }
        }
        LinearProgressIndicator(
            progress = { item.percentage },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = methodColor,
            trackColor = methodColor.copy(alpha = 0.12f),
        )
    }
}

@Composable
private fun BestSellersCard(bestSellers: List<BestSellerProduct>) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                text = "Productos más vendidos",
            )
            if (bestSellers.isEmpty()) {
                EmptyBestSellers()
            } else {
                bestSellers.take(BEST_SELLER_LIMIT).forEachIndexed { index, product ->
                    BestSellerItem(product, index + 1)
                    if (index < bestSellers.take(BEST_SELLER_LIMIT).lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    icon: ImageVector,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyBestSellers() {
    Box(
        modifier = Modifier.fillMaxWidth().height(80.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No hay datos disponibles",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BestSellerItem(
    product: BestSellerProduct,
    position: Int,
) {
    val productColor = Color(product.colorHex)
    val totalRevenue = product.salesCount * product.price

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = productColor.copy(alpha = 0.1f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = position.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = productColor,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        BestSellerDetails(
            product = product,
            productColor = productColor,
            totalRevenue = totalRevenue,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(10.dp))
        AdaptiveAmountText(
            text = money("$", product.price, separator = ""),
            modifier = Modifier.weight(BEST_SELLER_AMOUNT_WEIGHT),
            baseStyle =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                ),
            color = MaterialTheme.colorScheme.primary,
            options = AdaptiveAmountOptions(minFontSizeSp = 10f),
        )
    }
}

@Composable
private fun BestSellerDetails(
    product: BestSellerProduct,
    productColor: Color,
    totalRevenue: Double,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = product.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${product.salesCount} ventas · Total ${money("$", totalRevenue)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { product.progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = productColor,
            trackColor = productColor.copy(alpha = 0.12f),
        )
    }
}

private fun money(
    currency: String,
    amount: Double,
    separator: String = " ",
): String = "$currency$separator${String.format(Locale.getDefault(), "%.2f", amount)}"
