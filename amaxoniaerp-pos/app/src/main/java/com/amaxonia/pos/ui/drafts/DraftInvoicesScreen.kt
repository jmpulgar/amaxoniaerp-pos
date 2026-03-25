package com.amaxonia.pos.ui.drafts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.data.local.db.DraftInvoiceDao
import com.amaxonia.pos.data.local.db.DraftInvoiceEntity
import com.amaxonia.pos.data.repository.CartRepository
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.PriceLevel
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.theme.AmaxoniaBlue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DraftInvoicesViewModel(
    private val draftInvoiceDao: DraftInvoiceDao,
    private val cartRepository: CartRepository
) : ViewModel() {

    private val _drafts = MutableStateFlow<List<DraftInvoiceEntity>>(emptyList())
    val drafts: StateFlow<List<DraftInvoiceEntity>> = _drafts.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadDrafts()
    }

    fun loadDrafts() {
        viewModelScope.launch {
            _isLoading.value = true
            _drafts.value = draftInvoiceDao.getAll()
            _isLoading.value = false
        }
    }

    fun deleteDraft(id: String) {
        viewModelScope.launch {
            draftInvoiceDao.deleteById(id)
            loadDrafts()
        }
    }

    /** Carga un borrador en el carrito actual para facturarlo */
    fun loadDraftIntoCart(draft: DraftInvoiceEntity): Boolean {
        return try {
            cartRepository.clearCart()
            val jsonArray = JSONArray(draft.itemsJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val product = Product(
                    id = obj.getString("productId"),
                    description = obj.getString("description"),
                    code = obj.optString("code", ""),
                    barcode1 = obj.optString("barcode1", ""),
                    taxRate = obj.optDouble("taxRate", 0.0),
                    isExempt = obj.optBoolean("isExempt", false),
                    prices = listOf(
                        PriceLevel(
                            label = "A",
                            pricePlusTax = obj.getDouble("unitPriceWithTax")
                        )
                    )
                )
                val qty = obj.getInt("quantity")
                val discount = obj.optDouble("discountPercent", 0.0)

                // Agregar al carrito con la cantidad correcta
                cartRepository.addToCart(product)
                repeat(qty - 1) { cartRepository.increaseQuantity(product.id) }
                if (discount > 0.0) {
                    cartRepository.updateItemDiscount(product.id, discount)
                }
                val unitPrice = obj.getDouble("unitPriceWithTax")
                if (unitPrice > 0.0) {
                    cartRepository.updateItemPrice(product.id, unitPrice)
                }
            }
            // Borrar el borrador despues de cargarlo
            viewModelScope.launch {
                draftInvoiceDao.deleteById(draft.id)
                loadDrafts()
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftInvoicesScreen(
    onBack: () -> Unit,
    onDraftLoaded: () -> Unit, // Navegar al carrito despues de cargar
    viewModel: DraftInvoicesViewModel = injectedViewModel {
        DraftInvoicesViewModel(
            DependencyContainer.draftInvoiceDao,
            DependencyContainer.cartRepository
        )
    }
) {
    val drafts by viewModel.drafts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Facturas Pendientes",
                        fontWeight = FontWeight.Bold,
                        color = AmaxoniaBlue
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = AmaxoniaBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AmaxoniaBlue)
            }
        } else if (drafts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No hay facturas pendientes",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(drafts, key = { it.id }) { draft ->
                    DraftInvoiceCard(
                        draft = draft,
                        onLoad = {
                            val success = viewModel.loadDraftIntoCart(draft)
                            if (success) {
                                onDraftLoaded()
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Error al cargar borrador")
                                }
                            }
                        },
                        onDelete = { viewModel.deleteDraft(draft.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DraftInvoiceCard(
    draft: DraftInvoiceEntity,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val dateStr = dateFormat.format(Date(draft.createdAt))
    val clientName = if (!draft.clientFirstName.isNullOrBlank()) {
        "${draft.clientFirstName} ${draft.clientLastName.orEmpty()}".trim()
    } else {
        "Sin cliente"
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        clientName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${draft.itemCount} productos - $dateStr",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!draft.sellerName.isNullOrBlank()) {
                        Text(
                            "Vendedor: ${draft.sellerName}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    "$ ${String.format("%.2f", draft.total)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AmaxoniaBlue
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onLoad,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmaxoniaBlue)
                ) {
                    Text("Cargar al Carrito")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar borrador",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
