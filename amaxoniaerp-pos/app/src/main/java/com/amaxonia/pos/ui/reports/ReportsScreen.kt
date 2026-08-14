package com.amaxonia.pos.ui.reports

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Discount
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.amaxonia.pos.domain.model.BestSellerProduct
import com.amaxonia.pos.domain.model.SummaryStats
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.theme.PosPalette
import com.amaxonia.pos.ui.theme.ReportOrange
import com.amaxonia.pos.ui.theme.SuccessGreen
import java.util.Locale

private const val BEST_SELLER_LIMIT = 10
private const val BEST_SELLER_AMOUNT_WEIGHT = 0.6f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = injectedViewModel { ReportsViewModel(DependencyContainer.reportRepository) },
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
                    state.summary?.let { summary ->
                        item { HeroSummaryCard(summary) }
                        item { MetricCardsRow(summary) }
                        item { TransactionBreakdownCard(summary) }
                    }
                    item { BestSellersCard(state.bestSellers) }
                }
            }
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
private fun HeroSummaryCard(summary: SummaryStats) {
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
                    tint = PosPalette.FixedWhite.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ventas totales",
                    style = MaterialTheme.typography.labelLarge,
                    color = PosPalette.FixedWhite.copy(alpha = 0.8f),
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
            options = com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                minFontSizeSp = 18f,
            ))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SummaryValue(
                    label = "Ventas brutas",
                    value = money(summary.moneda, summary.grossSales),
                    modifier = Modifier.weight(1f),
                )
                SummaryValue(
                    label = "Total facturas",
                    value = summary.totalTransactions.toString(),
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
            color = PosPalette.FixedWhite.copy(alpha = 0.65f),
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
        options = com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
            minFontSizeSp = 10f,
        ))
    }
}

@Composable
private fun MetricCardsRow(summary: SummaryStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetricMiniCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.ShoppingCart,
            iconTint = MaterialTheme.colorScheme.primary,
            label = "Ticket promedio",
            value = money(summary.moneda, summary.ticketPromedio),
        )
        MetricMiniCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.Discount,
            iconTint = ReportOrange,
            label = "Descuentos",
            value = money(summary.moneda, summary.discounts),
        )
    }
}

@Composable
private fun MetricMiniCard(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AdaptiveAmountText(
                text = value,
                modifier = Modifier.fillMaxWidth(),
                baseStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            options = com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                minFontSizeSp = 10f,
            ))
        }
    }
}

@Composable
private fun TransactionBreakdownCard(summary: SummaryStats) {
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
                icon = Icons.Rounded.Receipt,
                text = "Desglose de facturas",
            )
            TransactionStatusRow(
                icon = Icons.Rounded.CheckCircle,
                label = "Pagadas",
                count = summary.totalPaid,
                color = SuccessGreen,
            )
            ProgressBar(
                progress = ratio(summary.totalPaid, summary.totalTransactions),
                color = SuccessGreen,
                trackColor = MaterialTheme.colorScheme.tertiaryContainer,
            )
            TransactionStatusRow(
                icon = Icons.Rounded.Cancel,
                label = "Anuladas",
                count = summary.totalCancelled,
                color = MaterialTheme.colorScheme.error,
                amount = if (summary.cancellations > 0) money(summary.moneda, summary.cancellations) else null,
            )
            ProgressBar(
                progress = ratio(summary.totalCancelled, summary.totalTransactions),
                color = MaterialTheme.colorScheme.error,
                trackColor = MaterialTheme.colorScheme.errorContainer,
            )
        }
    }
}

@Composable
private fun TransactionStatusRow(
    icon: ImageVector,
    label: String,
    count: Int,
    color: Color,
    amount: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        amount?.let {
            AdaptiveAmountText(
                text = it,
                modifier = Modifier.weight(1f),
                baseStyle = MaterialTheme.typography.labelMedium.copy(textAlign = TextAlign.End),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            options = com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                minFontSizeSp = 9f,
            ))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun ProgressBar(
    progress: Float,
    color: Color,
    trackColor: Color,
) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = color,
        trackColor = trackColor,
    )
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
            options =
                com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                    minFontSizeSp = 10f,
                ),
        )
    }
}

@Composable
private fun BestSellerDetails(
    product: BestSellerProduct,
    productColor: Color,
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
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${product.salesCount} ventas",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(10.dp))
            LinearProgressIndicator(
                progress = { product.progress },
                modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = productColor,
                trackColor = productColor.copy(alpha = 0.1f),
            )
        }
    }
}


private fun money(
    currency: String,
    amount: Double,
    separator: String = " ",
): String = "$currency$separator${String.format(Locale.getDefault(), "%.2f", amount)}"

private fun ratio(
    value: Int,
    total: Int,
): Float = if (total > 0) value.toFloat() / total else 0f
