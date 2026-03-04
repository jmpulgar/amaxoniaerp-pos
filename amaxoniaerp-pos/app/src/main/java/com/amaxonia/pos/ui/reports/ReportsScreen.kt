package com.amaxonia.pos.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.theme.AmaxoniaBlue
import com.amaxonia.pos.ui.theme.BgLightGray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = injectedViewModel { ReportsViewModel(DependencyContainer.reportRepository) },
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = BgLightGray,
        topBar = {
            TopAppBar(
                title = { Text("Reportes", fontWeight = FontWeight.Bold, color = AmaxoniaBlue) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = AmaxoniaBlue)
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
                .padding(horizontal = 16.dp)
        ) {
            if (state.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            state.error ?: "Error desconocido",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = { viewModel.retry() }) {
                            Text("Reintentar")
                        }
                    }
                }
            }
            if (state.isLoading && state.summary.totalTransactions == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AmaxoniaBlue)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    item {
                        SummaryStatsCard(summary = state.summary)
                    }
                    item {
                        BestSellersCard(bestSellers = state.bestSellers)
                    }
                    item {
                        PaymentMethodCard(paymentStats = state.paymentStats)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatsCard(summary: com.amaxonia.pos.domain.model.SummaryStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BarChart, null, tint = AmaxoniaBlue, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Resumen General", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF2A3256))
            }
            Spacer(modifier = Modifier.height(20.dp))
            StatRow("Ventas Brutas", "$${String.format("%.2f", summary.grossSales)}", AmaxoniaBlue)
            Spacer(modifier = Modifier.height(12.dp))
            StatRow("Ventas Netas", "$${String.format("%.2f", summary.netSales)}", Color(0xFF4CAF50))
            Spacer(modifier = Modifier.height(12.dp))
            StatRow("Descuentos", "$${String.format("%.2f", summary.discounts)}", Color(0xFFFF9800))
            Spacer(modifier = Modifier.height(12.dp))
            StatRow("Cancelaciones", "$${String.format("%.2f", summary.cancellations)}", Color(0xFFF44336))
            Spacer(modifier = Modifier.height(12.dp))
            StatRow("Total Transacciones", "${summary.totalTransactions}", Color(0xFF9E9E9E))
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = Color(0xFF7B83A7))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun BestSellersCard(bestSellers: List<com.amaxonia.pos.domain.model.BestSellerProduct>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TrendingUp, null, tint = AmaxoniaBlue, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Productos Más Vendidos", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF2A3256))
            }
            Spacer(modifier = Modifier.height(20.dp))
            if (bestSellers.isEmpty()) {
                Text("No hay datos disponibles", fontSize = 14.sp, color = Color.Gray)
            } else {
                bestSellers.forEachIndexed { index, product ->
                    BestSellerItem(product, index + 1)
                    if (index < bestSellers.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BestSellerItem(product: com.amaxonia.pos.domain.model.BestSellerProduct, position: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Surface(
                color = Color(product.colorHex).copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "$position",
                        fontWeight = FontWeight.Bold,
                        color = Color(product.colorHex),
                        fontSize = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2A3256))
                Text("${product.salesCount} ventas", fontSize = 12.sp, color = Color.Gray)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$${String.format("%.2f", product.price)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AmaxoniaBlue)
            LinearProgressIndicator(
                progress = product.progress,
                modifier = Modifier.width(60.dp).height(4.dp),
                color = Color(product.colorHex),
                trackColor = Color.LightGray
            )
        }
    }
}

@Composable
private fun PaymentMethodCard(paymentStats: com.amaxonia.pos.domain.model.PaymentMethodStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BarChart, null, tint = AmaxoniaBlue, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Método de Pago", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF2A3256))
            }
            Spacer(modifier = Modifier.height(20.dp))
            StatRow("Método", paymentStats.method, AmaxoniaBlue)
            Spacer(modifier = Modifier.height(12.dp))
            StatRow("Monto Total", "$${String.format("%.2f", paymentStats.amount)}", Color(0xFF4CAF50))
            Spacer(modifier = Modifier.height(12.dp))
            StatRow("Cantidad", "${paymentStats.count}", Color(0xFF9E9E9E))
            Spacer(modifier = Modifier.height(12.dp))
            StatRow("Porcentaje", "${paymentStats.percentage}%", AmaxoniaBlue)
        }
    }
}
