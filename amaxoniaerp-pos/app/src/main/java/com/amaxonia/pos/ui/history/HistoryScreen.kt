package com.amaxonia.pos.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionStatus
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.history.HistoryViewModel
import com.amaxonia.pos.ui.theme.AmaxoniaBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = injectedViewModel { HistoryViewModel(DependencyContainer.transactionRepository) },
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = com.amaxonia.pos.ui.theme.BgLightGray,
        topBar = {
            TopAppBar(
                title = { Text("Historial Transacciones", fontWeight = FontWeight.Bold, color = AmaxoniaBlue) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = AmaxoniaBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = com.amaxonia.pos.ui.theme.BgLightGray)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            FilterBarComponent()
            Spacer(modifier = Modifier.height(16.dp))
        if (state.isLoading && state.transactions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AmaxoniaBlue)
            }
        } else if (state.error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.error ?: "Error desconocido",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.retry() }) {
                        Text("Reintentar")
                    }
                }
            }
        } else {
            TransactionList(transactions = state.transactions)
        }
        }
    }
}

@Composable
private fun FilterBarComponent() {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Filtros", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2A3256))
            IconButton(onClick = { /* TODO: Abrir filtros */ }) {
                Icon(Icons.Default.Tune, contentDescription = "Filtros", tint = AmaxoniaBlue)
            }
        }
    }
}

@Composable
private fun TransactionList(transactions: List<Transaction>) {
    var currentDateHeader: String? = null

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        transactions.forEach { transaction ->
            if (transaction.dateHeader != currentDateHeader) {
                item {
                    DateHeader(transaction.dateHeader)
                    currentDateHeader = transaction.dateHeader
                }
            }
            item {
                TransactionItem(transaction)
            }
        }
    }
}

@Composable
private fun DateHeader(date: String) {
    Text(
        text = date,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF7B83A7)
    )
}

@Composable
private fun TransactionItem(transaction: Transaction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = transaction.invoiceNumber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF2A3256)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color(transaction.status.colorHex).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = transaction.status.label,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(transaction.status.colorHex)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = transaction.time,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${transaction.currency} ${String.format("%.2f", transaction.amount)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = AmaxoniaBlue
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.graphicsLayer { alpha = 0.5f }
                )
            }
        }
    }
}
